let reg = /^(-3|-2|-1|0|[1-5])$/;
let input = document.querySelector('input[name="y"]');
let span = document.querySelector('#errorMessage');

// Загружаем сохраненные результаты при загрузке страницы
document.addEventListener('DOMContentLoaded', function() {
    loadSavedResults();
});

document.querySelector('.btn').onclick = function(e){
    e.preventDefault();
    if(!validate(reg, input.value)){
        notValid(input, span, 'Вы должны ввести целое число от -3 до 5');
    }else{
        valid(input, span, '');
        getResponse(e);
    }
}

function validate(regex, inp){
    return regex.test(inp);
}

function notValid(inp, element, message){
    inp.classList.add('is-invalid');
    element.innerHTML = message;
}

function valid(inp, element, message){
    inp.classList.remove('is-invalid');
    inp.classList.add('is-valid');
    element.innerHTML = message;
}

function handleCheckbox(clickedCheckbox) {
    const checkboxes = document.querySelectorAll('input[type="checkbox"][name="hidden-checkbox"]');

    if (clickedCheckbox.checked) {
        checkboxes.forEach(checkbox => {
            if (checkbox !== clickedCheckbox) {
                checkbox.checked = false;
            }
        });
    }
}

async function getResponse(ev){
    ev.preventDefault();

    try {
        const xCheckbox = document.querySelector('input[name="hidden-checkbox"]:checked');
        const yInput = document.querySelector('input[name="y"]');
        const rRadio = document.querySelector('input[name="hidden-radio"]:checked');

        if (!xCheckbox || !yInput || !rRadio) {
            notValid(input, span, 'Пожалуйста, заполните все поля');
            return;
        }

        const x = xCheckbox.value;
        const y = yInput.value;
        const r = rRadio.value;

        console.log('Отправляемые данные:', {x, y, r});

        const params = new URLSearchParams({
            r: r,
            x: x,
            y: y
        });

        const response = await fetch(`/fcgi-bin/app.jar?${params.toString()}`, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
            }
        });

        if (!response.ok) {
            throw new Error(`Ошибка HTTP: ${response.status} ${response.statusText}`);
        }

        const answer = await response.text();
        localStorage.setItem("session", answer);
        var res = JSON.parse(answer);

        console.log('Полный ответ от сервера:', res);

        if (res.error === 'all ok') {
            valid(input, span, '');

            const currentData = res.current;
            saveResultToStorage({
                x: currentData.x || x,
                y: currentData.y || y,
                r: currentData.r || r,
                timestamp: currentData.timestamp || new Date().toLocaleString(),
                workTime: currentData.workTime || 0,
                hit: currentData.hit || false
            });

            updateTable();
            updateDotPosition(currentData.x || x, currentData.y || y, currentData.r || r);

        } else {
            console.error('Ошибка сервера: ' + (res.error || 'Неизвестная ошибка'));
        }

    } catch (error) {
        console.error(`Ошибка: ${error.message}`);
    }
}

// Функция для сохранения результата в localStorage
function saveResultToStorage(resultData) {
    let savedResults = JSON.parse(localStorage.getItem('savedResults') || '[]');
    savedResults.unshift(resultData);

    if (savedResults.length > 100) {
        savedResults = savedResults.slice(0, 100);
    }

    localStorage.setItem('savedResults', JSON.stringify(savedResults));
}

function loadSavedResults() {
    const savedResults = JSON.parse(localStorage.getItem('savedResults') || '[]');

    if (savedResults.length > 0) {
        updateTable();
    }
}

function updateTable() {
    const savedResults = JSON.parse(localStorage.getItem('savedResults') || '[]');
    var table = document.getElementById("table");
    var tbody = table.getElementsByTagName("tbody")[0];

    tbody.innerHTML = '';
    savedResults.forEach(result => {
        var row = document.createElement("tr");
        var xCell = document.createElement("td");
        var yCell = document.createElement("td");
        var rCell = document.createElement("td");
        var timeCell = document.createElement("td");
        var worktimeCell = document.createElement("td");
        var isHitCell = document.createElement("td");

        xCell.innerText = result.x;
        yCell.innerText = result.y;
        rCell.innerText = result.r;
        timeCell.innerText = result.timestamp;
        worktimeCell.innerText = result.workTime ? result.workTime + ' мс' : '0 мс';

        if (result.hit === true) {
            isHitCell.innerText = "✓";
            isHitCell.style.color = "green";
            isHitCell.style.fontWeight = "bold";
        } else {
            isHitCell.innerText = "✗";
            isHitCell.style.color = "red";
            isHitCell.style.fontWeight = "bold";
        }
        row.appendChild(xCell);
        row.appendChild(yCell);
        row.appendChild(rCell);
        row.appendChild(timeCell);
        row.appendChild(worktimeCell);
        row.appendChild(isHitCell);

        tbody.appendChild(row);
    });
}

function updateDotPosition(x, y, r) {
    const dot = document.getElementById("dot");
    if (!dot) return;

    const svgX = 150 + (parseFloat(x) / parseFloat(r)) * 75;
    const svgY = 150 - (parseFloat(y) / parseFloat(r)) * 75;

    dot.setAttribute("cx", svgX);
    dot.setAttribute("cy", svgY);
    dot.style.display = 'block';
}

function clearHistory() {
    if (confirm('Вы уверены, что хотите очистить историю результатов?')) {
        localStorage.removeItem('savedResults');
        updateTable(); // Обновляем таблицу (очищаем её)
    }
}